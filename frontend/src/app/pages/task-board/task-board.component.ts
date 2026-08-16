import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { Task, TaskStatus } from '../../core/models';
@Component({standalone:true,imports:[CommonModule,RouterLink],templateUrl:'./task-board.component.html',styleUrl:'./task-board.component.scss'})
export class TaskBoardComponent implements OnInit {statuses:TaskStatus[]=['TODO','IN_PROGRESS','DONE','REVIEW'];tasks=signal<Task[]>([]);constructor(public auth:AuthService,private api:CompanyApiService){}ngOnInit(){this.load();}load(){this.api.tasks().subscribe(x=>this.tasks.set(x));}items(s:TaskStatus){return this.tasks().filter(x=>x.status===s);}move(t:Task,status:TaskStatus){this.api.updateTaskStatus(t.id,status).subscribe({next:()=>this.load(),error:()=>this.load()});}}
